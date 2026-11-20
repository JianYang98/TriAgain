import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


PREPARE = Path(__file__).resolve().parents[1] / "prepare"


class PrepareNextCommandsTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.home = Path(directory.name)
        self.bin = self.home / "bin"
        self.log_root = self.home / "logs"
        self.config = self.home / "prepare.json"
        self.config.write_text(json.dumps({
            "host": "localhost",
            "port": 5432,
            "database": "triagain_test",
            "user": "prepare_user",
            "schema": "public",
            "allowed_hosts": ["localhost"],
            "sslmode": "disable",
            "log_root": str(self.log_root),
        }))
        self.write_command("bin/psql", "printf '%s\\n' PREPARE_COMMIT_CONFIRMED")

    def write_command(self, path, command):
        script = self.home / path
        script.parent.mkdir(parents=True, exist_ok=True)
        script.write_text("#!/bin/sh\n" + command + "\n")
        script.chmod(0o700)

    def prepare(self, python_options=(), *, rps=150, requests=1200):
        command = [sys.executable, *python_options, str(PREPARE), "--config", str(self.config),
                   "--rps", str(rps), "--capacity", "10", "--requests", str(requests), "--warmup"]
        result = subprocess.run(command, text=True, capture_output=True,
                                env={**os.environ, "PATH": str(self.bin)})
        return result.returncode, result.stdout, result.stderr

    def test_success_prints_runnable_blocks_with_the_same_conditions(self):
        code, output, errors = self.prepare()
        self.assertEqual((code, errors), (0, ""))
        run_env = output.splitlines()[-1]
        self.assertRegex(run_env, r"^TARGET_RPS=150 REQUESTS=1200 RUN_ID=\d{8}-\d{6}-rps150-cap10-warmup$")
        run_id = run_env.split("RUN_ID=")[1]
        app_block, runner_block = re.findall(r"(?m)^\(\n[\s\S]*?^\)$", output)
        self.assertIn(f"[수집기 창 · 앱 EC2 · {run_id}]", output)
        self.assertIn(f"[k6 부하 테스트 창 · 러너 EC2 · {run_id}]", output)

        log_path, = sorted(self.log_root.glob("prepare/*.log"))
        log = log_path.read_text()
        self.assertIn(f"크루 준비 완료\n로그: {log_path}\n\n{run_env}\n\n------\n", log)
        self.assertNotIn("수집기 창", log)
        self.assertNotIn("k6 부하 테스트 창", log)

        self.write_command("load-test/collectors/collect", 'printf "collect %s\\n" "$*"')
        self.write_command("triagain-k6/load-test/scripts/run-rps-join-test.sh",
                           'printf "k6 %s %s %s %s %s\\n" '
                           '"$RUN_ID" "$TARGET_RPS" "$REQUESTS" "$DETAIL" "$BASE_URL"')
        runner_env = {"BASE_URL": "https://app.example.invalid", "TOKENS_FILE": "/tmp/test-tokens.csv",
                      "PRE_VUS": "200", "SESSION_DATE": "20261118"}
        app = self.run_block(app_block, {"APP_CONTAINER": "app-test"})
        self.assertEqual(app.returncode, 0, app.stderr)
        self.assertEqual(app.stdout.splitlines(), [
            "수집 대상 컨테이너: app-test", f"collect on --run-id {run_id} --container app-test",
        ])
        runner = self.run_block(runner_block, runner_env)
        self.assertEqual(runner.returncode, 0, runner.stderr)
        self.assertEqual(runner.stdout.splitlines(), [
            "k6 대상 앱: https://app.example.invalid", f"k6 {run_id} 150 1200 1 https://app.example.invalid",
        ])

        shell_only_base_url = self.run_block(
            f"BASE_URL='{runner_env['BASE_URL']}'\n{runner_block}",
            {name: value for name, value in runner_env.items() if name != "BASE_URL"},
        )
        self.assertEqual(shell_only_base_url.returncode, 0, shell_only_base_url.stderr)
        self.assertEqual(shell_only_base_url.stdout, runner.stdout)

        empty_base_url = self.run_block(runner_block, {**runner_env, "BASE_URL": ""})
        self.assertNotEqual(empty_base_url.returncode, 0)
        self.assertIn("export BASE_URL=", empty_base_url.stderr)
        self.assertEqual(empty_base_url.stdout, "")

        for setting in ("APP_CONTAINER", *runner_env):
            with self.subTest(missing=setting):
                if setting == "APP_CONTAINER":
                    block, env = app_block, {}
                else:
                    block = runner_block
                    env = {name: value for name, value in runner_env.items() if name != setting}
                result = self.run_block(block, env)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(setting, result.stderr)
                self.assertEqual(result.stdout, "")

    def run_block(self, block, env):
        return subprocess.run(["/bin/bash"], input=block, text=True, capture_output=True,
                              env={"HOME": str(self.home), "PATH": "/usr/bin:/bin", **env})

    def test_rejects_less_than_one_second_of_requests_before_psql(self):
        psql_called = self.home / "psql-called"
        self.write_command("bin/psql", f': > "{psql_called}"')
        code, output, errors = self.prepare(rps=100, requests=99)
        self.assertEqual((code, output), (2, ""))
        self.assertIn("최소 100건", errors)
        self.assertIn("99건", errors)
        self.assertFalse(psql_called.exists())
        self.assertFalse(self.log_root.exists())

    def test_accepts_one_second_of_requests(self):
        code, output, errors = self.prepare(rps=100, requests=100)
        self.assertEqual((code, errors), (0, ""))
        self.assertIn("TARGET_RPS=100 REQUESTS=100 ", output)

    def test_unconfirmed_commit_prints_no_commands(self):
        self.write_command("bin/psql", "printf '%s\\n' PREPARE_COMMIT_ATTEMPT")
        code, output, errors = self.prepare()
        self.assertEqual((code, output), (1, ""))
        self.assertIn("DB 반영 불명", errors)
        self.assertNotIn("수집기 창", errors)
        self.assertNotIn("k6 부하 테스트 창", errors)

    def test_commit_with_log_sync_failure_prints_no_commands(self):
        inject_log_failure = """
import runpy
import sys
from unittest.mock import patch

with patch("os.fsync", side_effect=[None, OSError("log sync failed")]):
    runpy.run_path(sys.argv.pop(1), run_name="__main__")
"""
        code, output, errors = self.prepare(("-c", inject_log_failure))
        self.assertEqual((code, output), (3, ""))
        self.assertIn("커밋은 확인됐지만 결과 로그 기록 실패", errors)
        self.assertNotIn("수집기 창", errors)
        self.assertNotIn("k6 부하 테스트 창", errors)


if __name__ == "__main__":
    unittest.main()

import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest


EXPORTER = Path(__file__).resolve().parents[2] / "collectors" / "prom-export.sh"
START = "2026-11-20T12:08:21.215+09:00"
END = "2026-11-20T12:15:12.354+09:00"
TIME_VARIABLES = {"START_AT", "END_AT", "START_MS", "END_MS"}


class PromExportTimeTest(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.capture = self.root / "curl-arguments"
        queries = self.root / "queries.promql"
        queries.write_text("up|up\n")

        fake_curl = self.root / "curl"
        fake_curl.write_text("""#!/bin/sh
set -eu
for arg in "$@"; do
    if [ "${previous:-}" = -o ]; then output=$arg; fi
    case "$arg" in start=*|end=*|step=*) printf '%s\\n' "$arg" >> "$MOCK_CURL_CAPTURE" ;; esac
    previous=$arg
done
printf '%s\\n' '{"status":"success","data":{"result":[{}]}}' > "$output"
printf '200'
""")
        fake_curl.chmod(0o700)
        self.env = {
            **{key: value for key, value in os.environ.items() if key not in TIME_VARIABLES},
            "PATH": f"{self.root}{os.pathsep}{os.environ.get('PATH', os.defpath)}",
            "MOCK_CURL_CAPTURE": str(self.capture),
            "PROM_URL": "http://prometheus.example.invalid",
            "QUERIES": str(queries),
            "TZ": "UTC",
        }

    def export(self, out_dir, **times):
        self.capture.unlink(missing_ok=True)
        return subprocess.run(
            ["/bin/bash", str(EXPORTER)],
            env={**self.env, **times, "OUT_DIR": str(out_dir)},
            text=True,
            capture_output=True,
            timeout=5,
        )

    def test_copied_timestamps_match_legacy_epoch_query(self):
        cases = {
            "epoch": {"START_MS": "1795144101215", "END_MS": "1795144512354"},
            "kst": {"START_AT": START, "END_AT": END},
            "padded": {"START_AT": f"\t {START}\u00a0", "END_AT": f" {END} \t"},
            "utc": {"START_AT": "2026-11-20T03:08:21.215Z", "END_AT": "2026-11-20T03:15:12.354Z"},
        }
        for name, times in cases.items():
            with self.subTest(name=name):
                out_dir = self.root / name
                result = self.export(out_dir, **times)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.capture.read_text().splitlines(), [
                    "start=1795144101.215", "end=1795144512.354", "step=1",
                ])
                self.assertTrue((out_dir / "prom-up.json").is_file())

    def test_accepts_seconds_and_short_fraction(self):
        result = self.export(
            self.root / "seconds",
            START_AT="2026-11-20T03:08:21Z",
            END_AT="2026-11-20T03:08:21.2Z",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.capture.read_text().splitlines()[:2], [
            "start=1795144101.000", "end=1795144101.200",
        ])

    def test_invalid_ranges_stop_before_query_or_output(self):
        cases = (
            ({"START_AT": START}, "END_AT에 시각"),
            ({"START_AT": " \t ", "END_AT": END}, "START_AT에 시각"),
            ({"START_AT": START, "END_MS": "1795144512354"}, "한 쌍만"),
            ({"START_AT": "2026-11-20T12:08:21.215", "END_AT": END}, "시간대를 포함"),
            ({"START_AT": "2026-11-20 12:08:21.215+09:00", "END_AT": END}, "시간대를 포함"),
            ({"START_AT": "2026-02-30T12:08:21.215+09:00", "END_AT": END}, "유효한 날짜"),
            ({"START_AT": "2026-11-20T12:08:21.215+09:99", "END_AT": END}, "시간대를 포함"),
            ({"START_AT": END, "END_AT": START}, "시작 시각보다 늦어야"),
            ({"START_AT": START, "END_AT": START}, "시작 시각보다 늦어야"),
            ({"START_MS": "1795144101215", "END_MS": "nope"}, "13자리"),
        )
        out_dir = self.root / "invalid"
        for times, error in cases:
            with self.subTest(times=times):
                result = self.export(out_dir, **times)
                self.assertEqual(result.returncode, 1, result.stderr)
                self.assertIn(error, result.stderr)
                self.assertFalse(self.capture.exists())
                self.assertFalse(out_dir.exists())

    def test_preserves_existing_export(self):
        out_dir = self.root / "existing"
        out_dir.mkdir()
        exported = out_dir / "prom-up.json"
        exported.write_text("previous export")
        result = self.export(out_dir, START_AT=START, END_AT=END)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertEqual(exported.read_text(), "previous export")
        self.assertFalse(self.capture.exists())


if __name__ == "__main__":
    unittest.main()

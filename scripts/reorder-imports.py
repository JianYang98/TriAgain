#!/usr/bin/env python3
"""import 를 checkstyle ImportOrder 설정 순서로 재배치한다. import 를 추가·삭제하지 않는다.

    python3 scripts/reorder-imports.py --dry-run $(find src/main/java -name '*.java')
    python3 scripts/reorder-imports.py           $(find src/main/java -name '*.java')

그룹 판정은 `ImportOrder` 를 그대로 옮긴 것이다. prefix startsWith 가 아니다 —
평문 그룹은 `^\\Q…\\E` 로 앵커되지만 정규식 그룹은 앵커 없이 find() 로 동작하고,
**매치 시작 위치가 가장 앞인 그룹**이 이긴다(동률이면 매치 끝이 더 뒤인 쪽). 그래서
`com.triagain.X` 는 `com\\.(?!…naver)` 가 0번 위치에서 잡아 catch-all(1번 위치의 폭 0
매치)을 이기고, `com.naver.X` 는 그 lookahead 에 막혀 `com.naver.` 그룹으로 떨어진다.

경계가 의심스러운 파일은 고치지 않고 신고한다 — 조용히 넘기면 어디가 깨졌는지
바이트코드 게이트까지 가서야 알게 된다.
"""
import argparse
import pathlib
import re
import sys

# rules.xml <module name="ImportOrder"> 의 groups 값과 순서·내용이 같아야 한다
GROUPS = [
	'java.',
	'javax.',
	'jakarta.',
	'org.',
	'net.',
	r'/com\.(?!nhncorp|navercorp|naver)/',
	r'/(?!java\.|javax\.|jakarta\.|com\.|org\.|net\.)/',
	'com.nhncorp.',
	'com.navercorp.',
	'com.naver.',
]


def compile_groups(specs):
	out = []
	for spec in specs:
		if spec.startswith('/') and spec.endswith('/'):
			out.append(re.compile(spec[1:-1]))          # 앵커 없음 — find() 의미
		else:
			pkg = spec if spec.endswith('.') else spec + '.'
			out.append(re.compile('^' + re.escape(pkg)))
	return out


PATTERNS = compile_groups(GROUPS)


def group_of(name):
	"""ImportOrder.getGroupNumber 와 같은 규칙. 어디에도 안 걸리면 마지막 그룹 뒤."""
	best_idx, best_pos, best_end = len(PATTERNS), sys.maxsize, -1
	for i, pat in enumerate(PATTERNS):
		for m in pat.finditer(name):
			pos, end = m.start(), m.end()
			if pos < best_pos or (pos == best_pos and end > best_end):
				best_idx, best_pos, best_end = i, pos, end
	return best_idx


def name_of(stmt):
	"""'import static a.b.C.d;' → 'a.b.C.d'

	정렬 키는 반드시 이 이름이다. 문장 전체(';' 포함)로 정렬하면 안 된다 —
	'…UseCase.Command;' 와 '…UseCase;' 의 갈림길이 '.'(46) 대 ';'(59) 라
	중첩 클래스가 바깥 클래스보다 앞서고, checkstyle 은 이름으로 비교하므로
	짧은 쪽(접두사)이 먼저다. 실제로 이걸 틀려서 59건이 남았다.
	"""
	body = stmt[len('import '):]
	if body.startswith('static '):
		body = body[len('static '):]
	return body.rstrip(';').strip()


def build_block(stmts):
	"""static 블록 → 빈 줄 → 비어 있지 않은 그룹들(사이에 빈 줄 1개)."""
	statics = sorted((s for s in stmts if s.startswith('import static ')), key=name_of)
	plains = [s for s in stmts if not s.startswith('import static ')]

	buckets = {}
	for s in plains:
		buckets.setdefault(group_of(name_of(s)), []).append(s)

	blocks = ([statics] if statics else []) + [
		sorted(buckets[k], key=name_of) for k in sorted(buckets)
	]
	out = []
	for i, block in enumerate(blocks):
		if i:
			out.append('')
		out.extend(block)
	return out


def reorder(lines, path):
	"""(새 lines, 사유) — 사유가 있으면 건드리지 않은 것이다."""
	idx = [i for i, l in enumerate(lines) if l.startswith('import ')]
	if not idx:
		return lines, None

	lo, hi = idx[0], idx[-1]
	for i in range(lo, hi + 1):
		if not lines[i].startswith('import ') and lines[i].strip():
			return lines, f'{path}:{i + 1} — import 구역에 import 도 빈 줄도 아닌 줄'
	stmts = [lines[i].strip() for i in idx]
	for s in stmts:
		if not s.endswith(';'):
			return lines, f'{path} — 세미콜론으로 끝나지 않는 import: {s!r}'
	if len(set(stmts)) != len(stmts):
		return lines, f'{path} — 중복 import'

	block = build_block(stmts)
	if sorted(x for x in block if x) != sorted(stmts):   # 삭제·중복 생성 자체 검사
		return lines, f'{path} — 재배치 전후 import 집합이 다르다 (스크립트 결함)'
	return lines[:lo] + block + lines[hi + 1:], None


def main():
	ap = argparse.ArgumentParser(
		description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument('--dry-run', action='store_true', help='쓰지 않고 결과만 보고')
	ap.add_argument('files', nargs='+', metavar='FILE')
	args = ap.parse_args()

	changed, skipped, reasons = 0, 0, []
	for f in args.files:
		p = pathlib.Path(f)
		src = p.read_text(encoding='utf-8')
		new, reason = reorder(src.split('\n'), f)
		if reason:
			skipped += 1
			reasons.append(reason)
			continue
		out = '\n'.join(new)
		if out != src:
			changed += 1
			if not args.dry_run:
				p.write_text(out, encoding='utf-8')

	print(f'{"[dry-run] " if args.dry_run else ""}{changed} 파일 변경 / {skipped} 파일 보류')
	for r in reasons:
		print('  보류:', r)
	return 1 if reasons else 0


if __name__ == '__main__':
	sys.exit(main())

"""선행 공백을 tabWidth=4 기준으로 탭 변환한다. 그 외 문자는 손대지 않는다.

사용법:
    python3 scripts/tabify.py <파일...>
    python3 scripts/tabify.py --preserve-text-blocks <파일...>

--preserve-text-blocks:
    Java 텍스트 블록(\"\"\") 내부를 변환하지 않는다. 텍스트 블록은 선행 공백이
    문자열의 일부이고, **닫는 구분자 줄의 들여쓰기도** incidental-whitespace
    계산에 참여하므로(JLS 3.10.6) 여는 줄 다음부터 닫는 줄까지 전부 보존한다.
    여는 줄의 선행 공백은 일반 코드 인덴트라 변환해도 문자열이 안 바뀐다.

    전제: 한 줄에 \"\"\" 가 두 번 나오지 않는다(적용 대상 5개 파일에서 실측 확인).
"""
import sys, re, pathlib

TAB = 4


def tabify(line):
    m = re.match(r'^([ \t]+)', line)
    if not m:
        return line
    ws = m.group(1)
    col = 0
    for ch in ws:
        col = col + TAB - (col % TAB) if ch == '\t' else col + 1
    return '\t' * (col // TAB) + ' ' * (col % TAB) + line[len(ws):]


def conv(path, preserve_text_blocks=False):
    src = pathlib.Path(path).read_text(encoding='utf-8')
    out = []
    inside = False
    for line in src.split('\n'):
        if preserve_text_blocks:
            if inside:
                out.append(line)                      # 문자열 내용 — 보존
                if '"""' in line:
                    inside = False                    # 닫는 구분자 줄까지 보존하고 빠져나온다
                continue
            out.append(tabify(line))
            if '"""' in line:
                inside = True
        else:
            out.append(tabify(line))
    pathlib.Path(path).write_text('\n'.join(out), encoding='utf-8')


args = sys.argv[1:]
preserve = '--preserve-text-blocks' in args
for p in [a for a in args if not a.startswith('--')]:
    conv(p, preserve)

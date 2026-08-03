#!/usr/bin/env python3
"""선행 공백을 tabWidth=4 기준으로 탭 변환한다. 그 외 문자는 손대지 않는다.

    python3 scripts/tabify.py $(git ls-files '*.java')

텍스트 블록(\"\"\") 내부는 **항상** 보존한다 — 선행 공백이 문자열 값의 일부라
변환하면 SQL이 조용히 바뀐다(JLS 3.10.6: 닫는 구분자 줄의 들여쓰기도
incidental-whitespace 계산에 참여하므로 닫는 줄까지 보존한다).

보존을 끄는 옵션은 두지 않는다. 끄고 싶은 상황이 없을뿐더러, 제외 대상을
손으로 관리하면 새 텍스트 블록이 조용히 변조된다. 반대 방향의 실수(보존이
과해 스페이스가 남음)는 maxWarnings=0 이라 빌드가 시끄럽게 깨진다.

경계는 줄 단위 토글로 잡으므로 **주석 안의 \"\"\" 에 속는다.** 홀수 개면
"닫히지 않았다"로 죽지만, 짝수 개가 진짜 텍스트 블록을 감싸면 경고 없이
통과하며 그 본문이 탭화된다 — 4스페이스가 1탭이 되어 상대 들여쓰기가
압축되므로 문자열 **값 자체가 바뀐다**. 그래서 주석 안 \"\"\" 를 만나면
고치지 않고 죽는다. 판별이 애매하면 사람을 부르는 게 이 스크립트의 방침이다.

블록 주석 상태는 렉서 없이 `/*`·`*/` 의 마지막 위치로만 근사한다. 문자열
리터럴 안의 `/*` 를 주석 시작으로 오해할 수 있는데, 그 방향의 실수는 멀쩡한
파일에서 죽는 것이라 사람이 보게 된다 — 조용히 변조되는 반대 방향보다 낫다.

이 가드를 고치거나 스크립트를 다시 돌릴 땐 프로브부터 재실행한다.
거절 10종 · 통과 1종 · 음성 대조 1종의 실측이 PR #137 본문에 있다.
"""
import argparse
import pathlib

TAB = 4


def tabify(line):
    body = line.lstrip(' \t')
    ws = line[:len(line) - len(body)]
    col = len(ws.expandtabs(TAB))
    return '\t' * (col // TAB) + ' ' * (col % TAB) + body


def convert(text, path):
    out, inside, in_block = [], False, False
    for no, line in enumerate(text.split('\n'), 1):
        if line.count('"""') > 1:
            raise SystemExit(f'{path}:{no} — 한 줄에 """ 가 2회. 토글 판별 불가하니 수동 확인.')
        if '"""' in line:
            head = line.split('"""')[0]
            if in_block or '//' in head or '/*' in head:
                raise SystemExit(f'{path}:{no} — 주석 안에 """ 가 있다. 토글이 뒤집히니 수동 확인.')
        out.append(line if inside else tabify(line))
        if '"""' in line:
            inside = not inside
        if not inside:                       # 텍스트 블록 본문의 /* 는 주석이 아니다
            opened, closed = line.rfind('/*'), line.rfind('*/')
            if opened > closed:
                in_block = True
            elif closed > opened:
                in_block = False
    if inside:
        raise SystemExit(f'{path} — 텍스트 블록이 닫히지 않았다.')
    return '\n'.join(out)


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('files', nargs='+', metavar='FILE')
    changed = 0
    for f in ap.parse_args().files:
        p = pathlib.Path(f)
        src = p.read_text(encoding='utf-8')
        out = convert(src, f)
        if out != src:                       # 내용이 같으면 mtime 도 건드리지 않는다
            p.write_text(out, encoding='utf-8')
            changed += 1
    print(f'{changed} 파일 변경')


if __name__ == '__main__':
    main()

---
name: triagain-pr-review
description: Run TriAgain's repository PR review workflow for requests such as "PR 리뷰해줘", /pr-review, /pr-review-fix, /pr-review-check, or a named project reviewer. Do not use for ordinary ad hoc code review.
---

# TriAgain PR review

1. Resolve the requested workflow to its exact basename under `.claude/commands/`.
2. Accept only `pr-review`, `pr-review-fix`, `pr-review-check`, or a basename ending in `-reviewer`. If the exact Markdown file does not exist, stop instead of guessing a replacement.
3. Read that live command file completely immediately before execution and follow it as the canonical workflow. When `pr-review.md` selects reviewers, read each selected reviewer file completely before applying it.
4. Run from the repository root and obey `AGENTS.md` plus the user's authorization boundaries.

Do not copy command contents into this skill. Claude-specific frontmatter such as `model` is metadata, not a Codex model-selection instruction.

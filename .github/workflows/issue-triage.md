---
emoji: 🏷️
description: Triage new issues by type and priority, detect duplicates, ask clarifying questions, and assign owners.
on:
  issues:
    types: [opened]
permissions:
  contents: read
  issues: read
  pull-requests: read
tools:
  github:
    mode: gh-proxy
    toolsets: [default]
safe-outputs:
  add-labels:
    max: 3
  add-comment:
    max: 2
  assign-to-user:
    max: 2
---

# Issue Triage

## Task

You are triaging a newly opened issue in this repository.

1. Read the full issue title and body.
2. Classify the issue type and apply exactly one type label when possible:
   - `type/bug`
   - `type/feature`
   - `type/docs`
   - `type/question`
3. Classify urgency and apply exactly one priority label when possible:
   - `priority/p0` for critical outages, security incidents, or data-loss risk.
   - `priority/p1` for major user-impacting defects or blocked key workflows.
   - `priority/p2` for standard defects or important improvements with moderate impact.
   - `priority/p3` for low-impact requests, polish, or informational follow-ups.
4. Search open and recently closed issues for likely duplicates by comparing title, symptoms, and reproduction details.
5. If this issue appears to be a duplicate:
   - add the `duplicate` label,
   - post a concise comment linking the most likely duplicate issue numbers and explaining why,
   - keep the comment actionable and polite.
6. If the issue description is unclear or missing critical information, post one concise comment with targeted clarifying questions.
7. Assign the issue to the best team member(s) based on repository ownership signals (CODEOWNERS, prior issue ownership, and recent work in the affected area inferred from mentioned components, endpoints, features, labels, stack traces, or keywords).
8. If no owner can be determined confidently, assign to a general triage owner if one is obvious from recent triage patterns; otherwise do not force assignment.

## Safe Outputs

- Use configured safe outputs for labels, comments, and assignment.
- Use `noop` with a short explanation when no visible action is required.

# Security Policy

## Supported versions

Only the **latest release** of CellSeg receives security fixes. Older releases are not maintained.

| Version | Supported |
|---|---|
| Latest release | ✅ |
| Older releases | ❌ |

## Scope

The following are in scope for security reports:

- The CellSeg Android application itself (`app/` module)
- Secrets handling (API token storage, EncryptedSharedPreferences)
- Network requests (model download, Gradio API communication)
- File access / path traversal in image import/export

The following are **out of scope**:

- Vulnerabilities in third-party dependencies (report those upstream)
- Physical access to a device (out of Android's threat model)
- Issues requiring root/ADB access to exploit

## Reporting a vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security issues by email to: `security@yaylali.uk`

Include in your report:

1. A description of the vulnerability and its potential impact
2. Steps to reproduce (proof of concept if applicable)
3. Affected version(s)
4. Any suggested mitigations

You should receive an acknowledgement within **7 days**.
If the issue is confirmed, a fix will be prepared and released as soon as practical.
Reporters will be credited in the release notes unless they prefer to remain anonymous.

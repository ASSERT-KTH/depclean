# Security Policy

## Supported Versions

Only the latest release of DepClean is supported with security updates.

| Version | Supported |
| ------- | --------- |
| 2.1.x   | ✅        |
| < 2.1   | ❌        |

## Reporting a Vulnerability

Please **do not open a public issue** for security vulnerabilities.

Instead, report it privately via [GitHub private vulnerability reporting](https://github.com/ASSERT-KTH/depclean/security/advisories/new). Include:

- A description of the vulnerability and its impact
- Steps to reproduce or a proof of concept
- The affected version(s)

You will receive a response as soon as possible. Once the issue is confirmed and a fix is available, a security advisory will be published and the fix released.

Note that DepClean is a build-time analysis tool: it does not run in production environments, and it does not modify your source code or original `pom.xml`. Vulnerabilities in DepClean's own dependencies are tracked continuously via Dependabot and SonarCloud.

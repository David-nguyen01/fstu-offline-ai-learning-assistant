# Security policy

## Reporting

Please do not open a public issue for a suspected vulnerability. Contact the
repository owner privately and include the affected component, reproduction
steps, impact, and a minimal proof of concept with all credentials removed.

## Secrets

- Use local `.env` files or deployment secret stores.
- Never commit JWT signing keys, database passwords, mail credentials,
  Cloudinary credentials, API keys, reset tokens, or session tokens.
- Rotate any credential immediately if it appears in Git history.
- Example configuration must contain placeholders only.

## User data

Uploaded documents, previews, extracted text, vector indexes, databases, logs,
and model caches are runtime data and must remain outside source control.

## Deployment baseline

- Use HTTPS and a restricted CORS allowlist outside local development.
- Provide a unique high-entropy JWT secret per environment.
- Run SQL Server and storage services on private networks.
- Restrict administrative and research endpoints by role.
- Back up the database and test restore procedures.
- Keep the Fine-tuned mode disabled until its adapter passes the behavioral gate.

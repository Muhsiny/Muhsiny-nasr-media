# Media Page Assistant

Android helper for a supervised Facebook Page setup workflow.

- Stores a local queue of page identities.
- Opens the official Facebook Page creation flow.
- Uses Android Accessibility (after explicit user enablement) to fill page name, category and bio and move through safe Next/Continue steps.
- Stops before the final Create button so the user confirms each real Page creation.
- Stops immediately when Facebook shows a security check, temporary block, CAPTCHA or similar restriction.
- Does not store Facebook passwords or tokens and does not bypass platform controls.

After installing the APK, open the app, save a queue, enable the Media Page Assistant accessibility service, then start the next item.

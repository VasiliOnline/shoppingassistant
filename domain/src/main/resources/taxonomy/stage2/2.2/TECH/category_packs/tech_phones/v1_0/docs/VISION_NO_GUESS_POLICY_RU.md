# TECH.PHONES vision no-guess policy

Allowed from image only: phone_type, coarse form factor, color, visible damage candidates.

Forbidden to infer from image only: exact brand, exact model, storage, carrier lock, account lock, IMEI/blacklist, battery health, repair history.

If evidence is insufficient, write `model_name_text` and `identity_resolution_status=MANUAL_REVIEW` rather than guessing a normalized model.

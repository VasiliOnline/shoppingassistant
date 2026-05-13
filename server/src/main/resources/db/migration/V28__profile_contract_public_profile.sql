ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS bio TEXT NULL,
    ADD COLUMN IF NOT EXISTS website TEXT NULL,
    ADD COLUMN IF NOT EXISTS public_profile_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS city_visible BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO user_profiles (
    user_id,
    display_name,
    avatar_url,
    city
)
SELECT
    au.id,
    au.display_name,
    au.avatar_url,
    au.city
FROM auth_users au
WHERE NOT EXISTS (
    SELECT 1
    FROM user_profiles up
    WHERE up.user_id = au.id
);

UPDATE user_profiles up
SET
    display_name = COALESCE(up.display_name, au.display_name),
    avatar_url = COALESCE(up.avatar_url, au.avatar_url),
    city = COALESCE(up.city, au.city)
FROM auth_users au
WHERE au.id = up.user_id;

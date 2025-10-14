CREATE
MATERIALIZED VIEW IF NOT EXISTS public.user_profile_read_model AS
SELECT u.id,
       u.name,
       u.phone_number,
       u.profile_image_url,
       u.status,
       u.phone_verified,
       u.email_verified,
       u.last_login_at,
       u.created_at,
       u.updated_at
FROM auth_oltp.users u WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_profile_read_model_id
    ON public.user_profile_read_model (id);

CREATE INDEX IF NOT EXISTS idx_user_profile_read_model_phone
    ON public.user_profile_read_model (phone_number);

CREATE INDEX IF NOT EXISTS idx_user_profile_read_model_status
    ON public.user_profile_read_model (status);

CREATE
OR REPLACE FUNCTION public.refresh_user_profile_read_model()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    REFRESH
MATERIALIZED VIEW CONCURRENTLY public.user_profile_read_model;
RETURN NULL;
END;
$$;
CREATE TRIGGER trigger_refresh_user_profile_read_model
    AFTER INSERT OR
UPDATE OR
DELETE
ON auth_oltp.users
    FOR EACH STATEMENT
EXECUTE FUNCTION public.refresh_user_profile_read_model();
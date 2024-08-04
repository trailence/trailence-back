# trailence-back

To launch it:
* Launch a PostgreSQL database
* Launch the Spring Boot application with the following environment variables:
    * TRAILENCE_JWT_SECRET to any secret
    * POSTGRESQL_HOST (default to localhost)
    * POSTGRESQL_PORT (default to 5432)
    * POSTGRESQL_USERNAME (default to postgres)
    * POSTGRESQL_PASSWORD (default to postgres)
    * SMTP_HOST (for example smtp.gmail.com)
    * SMTP PORT (for example 587)
    * SMTP_USERNAME
    * SMTP_PASSWORD
    * SMTP_AUTH_ENABLED
    * SMTP_TLS_ENABLED
    * And optionally if you want to create a user:
        * TRAILENCE_INIT_USER to the user's email
        * TRAILENCE_INIT_PASSWORD to the user's password
    * Optionally, if you want to use some external services:
        * GEONAMES_USER (username provided by geonames.org)
   
# trailence-back

To launch it:
* Launch a PostgreSQL database
* Launch the Spring Boot application with the following environment variables:
    * TRAILENCE_JWT_SECRET to any secret
    * POSTGRESQL_HOST (default to localhost)
    * POSTGRESQL_PORT (default to 5432)
    * POSTGRESQL_USERNAME (default to postgres)
    * POSTGRESQL_PASSWORD (default to postgres)
    * And optionally if you want to create a user:
        * TRAILENCE_INIT_USER to the user's email
        * TRAILENCE_INIT_PASSWORD to the user's password
   
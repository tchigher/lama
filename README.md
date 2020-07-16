Ledger Scala Template using typelevel stack
==========================================================================================================================================================================================

Typelevel Stack QuickStart
--------------------------

1. Install [sbt][sbt]
2. Clone the repo
3. `cd ledger-scala-template`
4. Install [PostgreSQL][postgresql] and configure access for user `postgres` and password `postgres` (or change it in `Module`)
5. Create database `users` and table `api_user` (see `src/main/resources/db/migration/users.sql` or use `Flyway` as in the tests) or according to the config
6. `sbt test` (optional)
7. `sbt run`
8. `curl http://localhost:8080/users/USERNAME`

About Template
--------------

It contains the minimal code to get you started:

- `UserRepository`: Defines a method to find a user without commiting to a Monad (kind of tagless-final).
  - `PostgresUserRepository`: Implementation of the UserRepository interface using Doobie and PostgreSQL abstracting over the Effect `F[_]`.
- `UserService`: Business logic on top of the UserRepository again abstracting over the Effect `F[_]`.
- `UserHttpEndpoint`: Defines the http endpoints of the REST API making use of the UserService.
- `HttpErrorHandler`: Mapping business errors to http responses in a single place.
- `http` package: Includes custom Circe Json Encoders for value classes.
- `config` object: Includes model related to application config.
- `validation` object: Includes fields validation using `cats.data.ValidatedNel`.
- `Server`: The main application that wires all the components and provide the web server.
- `App`: The entrypoint

Acknowledgement
----------------
Be inpired greatly by https://github.com/profunktor/typelevel-stack.g8

[typelevel]: https://typelevel.org
[http4s]: http://http4s.org/
[doobie]: http://tpolecat.github.io/doobie/
[circe]: https://circe.github.io/circe/
[cats-effect]: https://github.com/typelevel/cats-effect
[cats]: https://typelevel.org/cats/
[fs2]: https://github.com/functional-streams-for-scala/fs2
[pureconfig]: http://pureconfig.github.io

[sbt]: http://www.scala-sbt.org/1.x/docs/Setup.html
[postgresql]: https://www.postgresql.org/download/


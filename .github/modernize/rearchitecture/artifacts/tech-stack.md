# Technology Stack

- **Language/runtime:** Java; Maven compiler source/target are both 21. The build was executed with OpenJDK 25.0.3 using `--release 21`.
- **Framework:** Spring Boot 3.3.5, inherited from `spring-boot-starter-parent`.
- **Build:** Maven; Spring Boot Maven plugin repackages the output as an executable JAR.
- **Dependencies declared in `pom.xml`:**
  - `spring-boot-starter-web`
  - `spring-boot-starter-thymeleaf`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-data-jpa`
  - H2 database (`runtime`)
  - `spring-boot-starter-test` (`test`)
- Dependency versions are managed by the Spring Boot parent and are not individually specified in the project POM.
- The inspected source uses Spring component scanning and Jakarta `@PostConstruct`. No other application framework or external ML library was found.
- `src/main/resources/application.properties` disables JPA Open Session in View and the Thymeleaf template-location startup check; no Thymeleaf templates currently exist.
- A Java 21 toolchain is not required for the current build in this environment: Maven compiled with JDK 25 targeting release 21.

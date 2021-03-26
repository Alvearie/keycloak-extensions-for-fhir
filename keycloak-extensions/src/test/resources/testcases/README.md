# Zerocode Tests
 - The functional or end to end tests can be designed and automated using open source technologies Zerocode/Selenium/Java/Junit.
 - Note: Selenium usage is limited to scenarios where keycloak/token interactions require browser agent dependency.
<br>
Zerocode is Open Source project - makes it easy to create, change, orchestrate and maintain automated tests with absolute
minimum overhead REST, SOAP, Kafka Real Time Data Streams and much more. Tests are defined in declarative json style/format
to improve sharing and enhance speed in writing end to end tests. <br>
_For more details:https://github.com/authorjapps/zerocode_

### Structure of a zerocode testcase
- Junit test is a basic shell, with ZeroCodeUnitRunner, which calls upon the declarative test case defined in json format(Example located at src/test/java/KeycloakLoginTest.java)
- Core test case design is within the json files(login test case example is located at src/test/resources/testcases/login folder)
- Input properties and parameters needed for the test cases are provided through properties file (refer to properties file: src/test/resources/kc_integration_test.properties)
- Tests are linked to the properties file using @TargetEnv annotation as referenced in KeycloakLoginTest class

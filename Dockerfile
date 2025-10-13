FROM openjdk:21-jdk

COPY target/orderservice-*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app.jar"]
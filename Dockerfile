# podman image tag 2c3c2f3bd00c jerven/sapfhir-cli:latest
# podman push jerven/sapfhir-cli:latest

FROM openjdk:11-jdk-slim
COPY target/sapfhir-cli-1.0-SNAPSHOT-jar-with-dependencies.jar \
     cli.jar

ENTRYPOINT ["java", "-jar"]
CMD ["cli.jar"]

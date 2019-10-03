FROM openjdk:11.0.4-jre-slim
MAINTAINER Murilo Pereira <murilo@murilopereira.com>

ADD target/uberjar/nu-event-processor-0.1.0-SNAPSHOT-standalone.jar /srv

ENTRYPOINT ["java", "-jar", "/srv/nu-event-processor-0.1.0-SNAPSHOT-standalone.jar"]

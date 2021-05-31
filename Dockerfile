FROM openjdk:11.0.4-jre-stretch
VOLUME /tmp
ADD target/*.jar francetransfert-worker-api.jar

# Install Clamav
RUN apt-get update && apt-get install -y clamav clamav-daemon
#RUN mkdir /run/clamav && chown clamav:clamav /run/clamav
COPY etc/clamav /etc/clamav/

EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/francetransfert-worker-api.jar"]

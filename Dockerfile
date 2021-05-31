FROM openjdk:11.0.4-jre-stretch
VOLUME /tmp
ADD target/*.jar francetransfert-worker-api.jar

# Install Clamav
RUN apt-get update && apt-get install -y clamav clamav-daemon
RUN sed -i 's/^Foreground .*$/Foreground true/g' /etc/clamav/freshclam.conf
RUN mkdir /run/clamav && chown clamav:clamav /run/clamav
RUN groupadd virusgroup
COPY etc/clamav /etc/clamav/
COPY bootstrap.sh /
RUN chmod +x bootstrap.sh

EXPOSE 8080

CMD ["/bootstrap.sh"]

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/francetransfert-worker-api.jar"]

FROM openjdk:11.0.4-jre-slim-buster
VOLUME /tmp
ADD target/*.jar francetransfert-worker-api.jar

# Debian Base to use
ENV DEBIAN_VERSION buster

# Install Clamav
RUN echo "deb http://http.debian.net/debian/ $DEBIAN_VERSION main contrib non-free" > /etc/apt/sources.list && \
    echo "deb http://http.debian.net/debian/ $DEBIAN_VERSION-updates main contrib non-free" >> /etc/apt/sources.list && \
    echo "deb http://security.debian.org/ $DEBIAN_VERSION/updates main contrib non-free" >> /etc/apt/sources.list && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y -qq \
        clamav-daemon \
        clamav-freshclam \
        libclamunrar9 \
        ca-certificates \
        netcat-openbsd \
        wget && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# permission juggling
RUN mkdir /run/clamav && chown clamav:clamav /run/clamav

RUN sed -i 's/^Foreground .*$/Foreground true/g' /etc/clamav/freshclam.conf
RUN groupadd virusgroup
COPY etc/clamav /etc/clamav/
COPY bootstrap.sh /
RUN chmod +x bootstrap.sh

EXPOSE 8080

CMD ["/bootstrap.sh"]

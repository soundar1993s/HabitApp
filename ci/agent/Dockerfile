FROM maven:3.9-eclipse-temurin-21 AS maven_tools
FROM alpine/helm:3.19.0 AS helm_tools
FROM blogapp-jenkins-agent:aws
USER root
COPY --from=maven_tools /usr/share/maven /opt/maven
COPY --from=helm_tools /usr/bin/helm /usr/local/bin/helm
ENV MAVEN_HOME=/opt/maven
ENV PATH=/opt/maven/bin:${PATH}
USER jenkins
RUN mvn --version && helm version --short

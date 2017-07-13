FROM openjdk:8-jre

COPY server/target/scala-2.11/otm-modeling-server.jar /srv/

EXPOSE 8777

ENTRYPOINT ["java"]
CMD ["-jar", "/srv/otm-modeling-server.jar"]

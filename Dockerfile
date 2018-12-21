FROM java:8
EXPOSE 8080

ENV PORT="8080"

ADD target/jute-demo-1.0.0-standalone.jar /jute.jar

CMD java -jar /jute.jar

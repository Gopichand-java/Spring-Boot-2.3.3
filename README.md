# Spring-Boot-2.3.3

- Using Buildpacks technology to build the docker images
  
   The easiest way to get started is to invoke mvn spring-boot:build-image on a project. 
   It is possible to automate the creation of an image whenever the package phase is invoked, as       shown in the following example:

      <build>
        <plugins>
          <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>
              <execution>
                <goals>
                  <goal>build-image</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
      
 
- Layered Jars

        A repackaged jar contains the application’s classes and dependencies in BOOT-INF/classes and BOOT-INF/lib respectively. For cases where a docker image needs to be built from the contents of the jar, it’s useful to be able to separate these directories further so that they can be written into distinct layers.

        Layered jars use the same layout as regular repackaged jars, but include an additional meta-data file that describes each layer. To use this feature, the layering feature must be enabled:

        <project>
          <build>
            <plugins>
              <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>2.3.0.RELEASE</version>
                <configuration>
                  <layers>
                    <enabled>true</enabled>
                  </layers>
                </configuration>
              </plugin>
            </plugins>
          </build>
        </project>

        By default, the following layers are defined:

        dependencies for any dependency whose version does not contain SNAPSHOT.

        spring-boot-loader for the jar loader classes.

        snapshot-dependencies for any dependency whose version contains SNAPSHOT.

        application for application classes and resources.

        The layers order is important as it determines how likely previous layers can be cached when part of the application changes. The default order is dependencies, spring-boot-loader, snapshot-dependencies, application. Content that is least likely to change should be added first, followed by layers that are more likely to change.

- Added Jar mode functionality to write proper dockerfiles

  When you create a jar containing the layers index file, the spring-boot-jarmode-layertools jar will be added as a dependency to your jar. With this jar on the classpath, you can launch your application in a special mode which allows the bootstrap code to run something entirely different from your application, for example, something that extracts the layers.

  Here’s how you can launch your jar with a layertools jar mode:
  $ java -Djarmode=layertools -jar my-app.jar
  This will provide the following output:
  Usage:
    java -Djarmode=layertools -jar my-app.jar

  Available commands:
    list     List layers from the jar that can be extracted
    extract  Extracts layers from the jar for image creation
    help     Help about any command
  The extract command can be used to easily split the application into layers to be added to the dockerfile. Here’s an example of a Dockerfile using jarmode.

  FROM adoptopenjdk:11-jre-hotspot as builder
  WORKDIR application
  ARG JAR_FILE=target/*.jar
  COPY ${JAR_FILE} application.jar
  RUN java -Djarmode=layertools -jar application.jar extract

  FROM adoptopenjdk:11-jre-hotspot
  WORKDIR application
  COPY --from=builder application/dependencies/ ./
  COPY --from=builder application/spring-boot-loader/ ./
  COPY --from=builder application/snapshot-dependencies/ ./
  COPY --from=builder application/application/ ./
  ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]

  Assuming the above Dockerfile is in the current directory, your docker image can be built with docker build ., or optionally specifying the path to your application jar, as shown in the following example:
  docker build --build-arg JAR_FILE=path/to/myapp.jar .

  This is a multi-stage dockerfile. The builder stage extracts the directories that are needed later. Each of the COPY commands relates to the layers extracted by the jarmode.
  Of course, a Dockerfile can be written without using the jarmode. You can use some combination of unzip and mv to move things to the right layer but jarmode simplifies that.

- Custom Layers Configuration

    Depending on your application, you may want to tune how layers are created and add new ones. This can be done using a separate configuration file that should be registered as shown below:
    <project>
      <build>
        <plugins>
          <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>2.3.4.RELEASE</version>
            <configuration>
              <layers>
                <enabled>true</enabled>
                <configuration>${project.basedir}/src/layers.xml</configuration>
              </layers>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </project>

    The configuration file describes how the jar can be separated into layers, and the order of those layers. The following example shows how the default ordering described above can be defined explicitly:
    
    <layers xmlns="http://www.springframework.org/schema/boot/layers"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.springframework.org/schema/boot/layers
                          https://www.springframework.org/schema/boot/layers/layers-2.3.xsd">
      <application>
        <into layer="spring-boot-loader">
          <include>org/springframework/boot/loader/**</include>
        </into>
        <into layer="application" />
      </application>
      <dependencies>
        <into layer="snapshot-dependencies">
          <include>*:*:*SNAPSHOT</include>
        </into>
        <into layer="dependencies" />
      </dependencies>
      <layerOrder>
        <layer>dependencies</layer>
        <layer>spring-boot-loader</layer>
        <layer>snapshot-dependencies</layer>
        <layer>application</layer>
        </layerOrder>
    </layers>
    
    The layers XML format is defined in three sections:
    •	The <application> block defines how the application classes and resources should be layered.
    •	The <dependencies> block defines how dependencies should be layered.
    •	The <layerOrder> block defines the order that the layers should be written.
    
    Nested <into> blocks are used within <application> and <dependencies> sections to claim content for a layer. 
    The blocks are evaluated in the order that they are defined, from top to bottom. Any content not claimed by an earlier block remains available for subsequent blocks to consider.
    The <into> block claims content using nested <include> and <exclude> elements. 
    The <application> section uses Ant-style patch matching for include/exclude expressions. 
    The <dependencies> section uses group:artifact[:version] patterns.
    If no <include> is defined, then all content (not claimed by an earlier block) is considered.
    If no <exclude> is defined, then no exclusions are applied.
    Looking at the <dependencies> example above, we can see that the first <into> will claim all SNAPSHOT dependencies for the snapshot-dependencies layer. The subsequent <into> will claim anything left (in this case, any dependency that is not a SNAPSHOT) for the dependencies layer.
    The <application> block has similar rules. First claiming org/springframework/boot/loader/** content for the spring-boot-loader layer. Then claiming any remaining classes and resources for the application layer.
    Graceful shutdown of the application :
    server.shutdown=graceful
    spring.lifecycle.timeout-per-shutdown-phase=20s

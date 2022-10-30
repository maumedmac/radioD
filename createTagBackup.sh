git pull
./gradlew clean shadowJar
docker build .
tagName=$(docker images | awk '{print $3}' | awk 'NR==2')
docker tag $tagName toadsy/radio:latest
docker login
docker push toadsy/radio:latest
echo "Done!

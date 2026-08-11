set -a
source .env
set +a

docker-compose up -d

cd backend/text-book-illustration
./mvnw spring-boot:run

#cd backend
#cd backend/text-book-illustration
#./mvnw spring-boot:run &
#
#cd ../frontend
#npm install
#npm run dev
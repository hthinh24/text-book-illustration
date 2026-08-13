set -a
source .env
set +a

docker-compose up -d

cd backend/text-book-illustration
./mvnw test

cd ../../frontend/text-book-illustration
npm install
npm test

echo ""
read -p "--- TEST DONE, PRESS ENTER TO EXIT ---"
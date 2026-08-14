#!/bin/bash

# Define colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}==========================================${NC}"
echo -e "${CYAN}   🚀 STARTING INTEGRATION TEST RUNNER    ${NC}"
echo -e "${CYAN}==========================================${NC}\n"

# 1. Environment & Docker
echo -e "${YELLOW}🔹 [1/4] Loading environment & starting Docker...${NC}"
set -a
source .env
set +a

docker-compose up -d
echo -e "${GREEN}✔ Docker services are up and running!${NC}\n"

# 2. Backend Test
echo -e "${YELLOW}🔹 [2/4] Running Backend Tests (Spring Boot)...${NC}"
cd backend/text-book-illustration
./mvnw test
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✔ Backend Tests PASSED!${NC}\n"
else
    echo -e "\033[0;31m✖ Backend Tests FAILED!${NC}\n"
fi

# 3. Frontend Test
echo -e "${YELLOW}🔹 [3/4] Installing Frontend Dependencies...${NC}"
cd ../../frontend/text-book-illustration
npm install

echo -e "\n${BLUE}==========================================${NC}"
echo -e "${BLUE}   🧪 [4/4] STARTING FRONTEND TESTS       ${NC}"
echo -e "${BLUE}==========================================${NC}\n"

npm test

# 4. Done
echo -e "\n${GREEN}==========================================${NC}"
echo -e "${GREEN}   ✅ ALL TEST SEQUENCES FINISHED         ${NC}"
echo -e "${GREEN}==========================================${NC}\n"

read -p "--- PRESS ENTER TO EXIT ---"
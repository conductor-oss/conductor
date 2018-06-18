
clean-db:
	docker volume rm grpc_conductor_mysql

compose-build:
	docker-compose -f docker-compose.dependencies.yaml -f docker-compose.apps.yaml  build

dependencies-up:
	docker-compose -f docker-compose.dependencies.yaml up -d

dependencies-down:
	docker-compose -f docker-compose.dependencies.yaml down

stack-up:
	docker-compose -f docker-compose.dependencies.yaml up -d
	sleep 30
	docker-compose -f docker-compose.dependencies.yaml -f docker-compose.apps.yaml  up

stack-down:
	docker-compose -f docker-compose.dependencies.yaml -f docker-compose.apps.yaml  down

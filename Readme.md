# SSORest Container Admin Guide

## Start sample user store
docker stack deploy -c .\docker-compose-userstore.yaml ustore

## Start policy engine
docker stack deploy -c .\docker-compose-pe.yaml pe

## Start Gatway
docker stack deploy -c .\docker-compose-gw.yaml gw

## Start Test app
docker stack deploy -c .\docker-compose-testapp.yaml testapp

## Scale up 
docker service scale pe_pe=2

## Check Container Status
docker service ls

## Stop the env
docker stack rm testapp

docker stack rm pe

docker stack rm gw

docker stack rm ustore


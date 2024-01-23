## Intro 
This document guides you on creating the policy store with mysql helm chart. 

## Pre-req
You need k8s cluster 


## Before starting
Clone the IDFC helm scripts from below
```sh
git clone git@github.com:tliang-idfc/helm-dev.git
```

## Deployment

Run below command
```sh
cd helm-dev
mysql/create-pstore.sh
```


Hit Below URL to access the phpadmin 
https://myphpadmin.k3s.demo/

This dns should be resolved as the LB external IP of your ingerss-controller. If not, update your dns registry. 

Change the values.yaml accordingly if you want to use a different url, or root dn. 
Default login:
root
password: Not@SecurePassw0rd (or password)


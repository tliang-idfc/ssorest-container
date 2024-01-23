## Intro

This is the deployment guide for SSORest Gateway in Kubernetes (k8s).

## Pre-req

- Ensure you have a Kubernetes cluster with at least 32GB of RAM. For local testing, create a k3s cluster by following [this guide](link-to-article).
- A user store is required, preferably LDAP-based. If unavailable, use the OpenLDAP helm chart to set up one in your cluster by following [these steps](ref_docs/openldap-ustore.md).
- A policy store is necessary, supporting MySQL and LDAP. If you need one, use [this guide](ref_docs/mysql-pstore.md) to create a MySQL Policy Store using a helm chart.

## Before you start

Clone the source from the public GitHub repository:

```sh 
git clone git@github.com:tliang-idfc/ssorest-container.git
```

## Deploy Policy Engine

Assuming you have a policy store and you are in the `ssorest-container` directory, navigate to `helm/ssorest-pe/custom-config`.

Edit the `application-pe-docker.properties` file to configure your policy store:

```properties
policy.engine.data.storage=mysql
policy.engine.data.db.url=jdbc:mysql://pstore-mysql.ssorest-pstore.svc.cluster.local:3306/ssorest
policy.engine.data.db.username=root
policy.engine.data.db.password={AES256}qldzVsdzbE0=:a3vuzgYQthHVC9kYd5NrlQ==:aqxeHmDYVpcdoxn7mI6e6w==
```

Note: Replace `pstore-mysql.ssorest-pstore.svc.cluster.local` with your helm chart's service name in the format `{service_name}.{namespace}.svc.cluster.local`.

Update the `values.yaml` file:

```yaml
replicaCount: 2
image:
  repository: tliangidfc/ssorest-pe
  tag: "latest"
  pullPolicy: Always
imagePullSecrets:
  - name: idfc-docker-pull-secret
ingress:
  enabled: true
  hosts:
    - host: ssorest-pe.k3s.demo
      paths:
        - path: /
          pathType: ImplementationSpecific
configMap:
  useCustom: true
  customName: ssorest-pe-custom-config
```

Change the ingress host name as needed. Example: For a LoadBalancer IP `192.168.1.206`, update DNS or `/etc/hosts`:

```
192.168.1.206 ssorest-pe.k3s.demo
```

Execute `deploy.sh` to launch the service. Test the deployment at https://ssorest-pe.k3s.demo/ using default credentials: Username `admin` and Password `password`.

## Deploy Gateway

Place the `license.xml` file in the `ssorest-gw/custom-config` folder. Update `server.properties` as needed. Edit the `values.yaml` file, specifically the ingress hostname:

```yaml
host: ssorest-gw.k3s.demo
```

Run `deploy.sh` to deploy the chart. Once ready, verify the setup at https://ssorest-gw.k3s.demo/ssorest/service/sso/status.

## Deploy Test App

Deploy the web application by executing `deploy.sh` in the relevant directory.

## Uninstall the Chart

To remove the deployed charts, run:

```sh
helm uninstall ssorest-pe -n ssorest-pe
helm uninstall ssorest-gw -n ssorest-gw
helm uninstall testweb -n testweb
```

## Confluence version

Refer to https://idfc.atlassian.net/wiki/spaces/SSOREST/pages/1180893185/Deployment+Guide+-+k8s
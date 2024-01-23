NAMESPACE=ssorest-gw
DEPLOYMENT_NAME=ssorest-gw
# Create namespace
if ! kubectl get ns "$NAMESPACE" > /dev/null 2>&1; then
    kubectl create ns "$NAMESPACE"
    echo "Namespace '$NAMESPACE' created."
else
    echo "Namespace '$NAMESPACE' already exists."
fi

# Create config map
kubectl create configmap $DEPLOYMENT_NAME-custom-config \
  --from-file=server.properties=custom-config/server.properties  \
  --from-file=filter.properties=custom-config/filter-container.properties  \
  --from-file=license.xml=custom-config/license.xml  \
  --from-file=keystore.jks=custom-config/keystore.jks.base64 \
  -n $NAMESPACE

# Deploy
helm install $DEPLOYMENT_NAME ./ssorest-gw-chart -f values.yaml -n $NAMESPACE
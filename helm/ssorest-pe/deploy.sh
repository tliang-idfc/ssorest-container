NAMESPACE=ssorest-pe
DEPLOYMENT_NAME=ssorest-pe
# Create namespace
if ! kubectl get ns "$NAMESPACE" > /dev/null 2>&1; then
    kubectl create ns "$NAMESPACE"
    echo "Namespace '$NAMESPACE' created."
else
    echo "Namespace '$NAMESPACE' already exists."
fi

# Create config map
kubectl create configmap $DEPLOYMENT_NAME-custom-config \
  --from-file=application.properties=custom-config/application-pe-docker.properties  \
  --from-file=keystore.jks=custom-config/keystore.jks.base64 \
  -n $NAMESPACE

# Deploy
helm install $DEPLOYMENT_NAME ./ssorest-pe-chart -f values.yaml -n $NAMESPACE
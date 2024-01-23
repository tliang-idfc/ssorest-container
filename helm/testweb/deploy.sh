# Create secret

# Create config map
kubectl create configmap testapp-custom-config \
  --from-file=filter.properties=custom-config/filter.properties \
  --from-file=testweb.xml=custom-config/testweb.xml \
  -n testweb

# Deploy
helm install testapp ./testapp-chart -f values.yaml
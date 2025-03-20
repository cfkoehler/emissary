# Description

## Setup local k3d cluster
Install k3d:
```bash
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
```
Create the cluster:
```bash
k3d cluster create emissary -c contrib/k3d/k3d-default.yaml
```
List nodes:
```bash
kubectl get nodes
NAME                    STATUS   ROLES                  AGE   VERSION
k3d-emissary-agent-0    Ready    <none>                 23s   v1.32.2+k3s1
k3d-emissary-agent-1    Ready    <none>                 23s   v1.32.2+k3s1
k3d-emissary-agent-2    Ready    <none>                 23s   v1.32.2+k3s1
k3d-emissary-server-0   Ready    control-plane,master   26s   v1.32.2+k3s1
```

## Install applications

### Emissary

### rabbitMq

### Prometheus

Install prometheus operator stack:
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
```

Port forward grafana dashboard:
```bash
export POD_NAME=$(kubectl --namespace monitoring get pod -l "app.kubernetes.io/name=grafana,app.kubernetes.io/instance=kube-prometheus-stack" -oname)
kubectl --namespace monitoring port-forward $POD_NAME 3000
```
username: `admin`
password: `kubectl --namespace monitoring get secrets kube-prometheus-stack-grafana -o jsonpath="{.data.admin-password}" | base64 -d ; echo`


docs and guides: https://k3d.io/stable/
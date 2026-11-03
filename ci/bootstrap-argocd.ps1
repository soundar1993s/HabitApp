param([string]$Helm = 'helm')
$ErrorActionPreference = 'Stop'
# Administrative bootstrap only. Jenkins never runs this script or kubectl.
New-Item -ItemType Directory -Force reports | Out-Null
& $Helm repo add argo https://argoproj.github.io/argo-helm
& $Helm repo update argo
# CRDs are installed separately to keep the administrative release payload bounded.
& $Helm template argocd argo/argo-cd --version 10.11.6 --namespace argocd -f argocd/helm-values.yaml --set crds.install=true > reports/argocd-bootstrap.yaml
if ($LASTEXITCODE -ne 0) { throw 'Argo CD chart rendering failed' }
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/argocd-rbac.yaml
kubectl apply --server-side -n argocd -f reports/argocd-bootstrap.yaml
if ($LASTEXITCODE -ne 0) { throw 'Argo CD administrative bootstrap failed' }
kubectl rollout status deployment/argocd-server -n argocd --timeout=180s
kubectl rollout status statefulset/argocd-application-controller -n argocd --timeout=180s
# After the first real ECR image tag is committed, apply argocd/project.yaml
# and argocd/application.yaml. Argo CD then owns all application deployment.

# HabitApp deployment runbook

## CI prerequisites

Use the dedicated `habitapp-linux` inbound agent. Build its image with `docker build -f ci/agent/Dockerfile -t habitapp-jenkins-agent:aws .`. The image adds Maven 3.9.16 and Helm 3.19.0 to the existing AWS-capable Java 21 agent. Ensure its Maven cache is owned by UID/GID 1000.

Configure a Pipeline from SCM using the user-owned repository, branch `*/main`, script `Jenkinsfile`. Configure the Git PathRestriction extension to include application source, pom.xml, Dockerfile, Jenkinsfile and ci files, and exclude `helm/habitapp/values.yaml`. The pipeline enables `H/5 * * * *` polling.

Store the three credential IDs listed in README in Jenkins. The CI IAM identity needs only authorization-token and image-push/read operations for the HabitApp ECR repository. It has no EKS permissions. The SSH deploy key must have repository write permission and be usable non-interactively; GitHub host keys are pinned in `ci/github_known_hosts`.

SonarQube project key is `habit-tracker`. The Maven scanner waits for the quality result; the following Jenkins Quality Gate stage validates the completed analysis before publication. The 30-day project-scoped analysis token is stored as `habitapp-sonar-token`.

## Administrative Kubernetes bootstrap

The demonstration reuses cluster blogapp-eks in ap-south-1. Argo CD is v3.5.6 from official chart 10.11.6. Its control plane was bootstrapped with chart-rendered manifests after Helm release-metadata writes timed out. CRDs were installed separately and retained. The application itself is deployed by Argo CD's Helm renderer; no separate Helm release owns the same resources.

`ci/bootstrap-argocd.ps1` records the administrative bootstrap procedure. It is not called by Jenkins. The AppProject restricts the Git source and destination namespace. `k8s/argocd-rbac.yaml` allows the controller to read resources in habitapp and write only the required workload kinds there.

After Jenkins publishes the first real image and commits its tag:

```sh
kubectl apply -f argocd/project.yaml
kubectl apply -f argocd/application.yaml
kubectl get application habitapp -n argocd
```

Argo CD renders the chart and synchronizes the Deployment, Service, ConfigMap, ServiceAccount, PDB and Ingress. EKS nodes pull from ECR with their existing node role. Secret values are not needed by this in-memory application; the chart supports an optional externally created Secret reference.

## Private dashboards

```sh
kubectl port-forward -n argocd svc/argocd-server 8082:80 --address localhost
kubectl port-forward -n monitoring svc/monitoring-grafana 3003:80 --address localhost
```

The Argo CD dashboard uses HTTP only through a private ClusterIP and local forward. On this connection the default WebSocket forward stalled; the supported `KUBECTL_PORT_FORWARD_WEBSOCKETS=false` transport restored access. Do not expose these dashboard ports publicly.

The public application ingress uses HTTP, ALB health checks, session affinity and a 30-second drain delay. The namespace enables ALB pod readiness-gate injection. Runtime roots are read-only, UID/GID is 10001, capabilities are dropped and privilege escalation is disabled.

## Update and rollback

Push an application source change. Jenkins publishes `<build-number>-<12-character-commit>` and commits the new Helm image values. Argo CD detects and deploys that Git commit automatically. Inspect the public `/actuator/info`, workload image and Argo revision to correlate the release.

For a failed configuration rollout, revert its Git commit and push the revert. Keep automated sync enabled; do not use kubectl rollout undo against Argo-owned resources. The verified exercise committed an unavailable image, observed ImagePullBackOff while two old replicas served requests, and reverted the commit to restore release 4.

## Monitoring and logs

Apply the dashboard ConfigMap in monitoring with label `grafana_dashboard=1`, using `monitoring/habitapp.json`. Prometheus supplies pod CPU, memory, state and restart metrics. Argo CD supplies sync and application health.

```sh
kubectl get pods -n habitapp -o wide
kubectl top pods -n habitapp
kubectl logs -n habitapp deployment/habitapp --tail=80
kubectl get events -n habitapp --sort-by=.metadata.creationTimestamp
```

`docs/check-endpoint.ps1` records individual timestamped HTTP/readiness observations. Saved logs and screenshots are in the separate Project-Evidence/HabitApp folder. Do not include credential files or Secret contents in submission evidence.

# HabitApp Java CI and GitOps on EKS

Java 21 / Spring Boot Habit Tracker with Maven tests, Jenkins CI, SonarQube, Amazon ECR, Helm and Argo CD deployment.

- Application: http://k8s-habitapp-habitapp-1e4c46765c-1708282587.ap-south-1.elb.amazonaws.com
- Source: https://github.com/soundar1993s/HabitApp
- Upstream application: https://github.com/Vennilavanguvi/HabitApp
- Verified release: `4-2da366c2e6ad`, Jenkins `HabitApp-GitOps-CI` build 4.

## Delivery workflow

GitHub main → Jenkins → Maven compile/test/package → SonarQube Quality Gate → Docker → ECR → update Helm values and push Git → Argo CD → EKS.

Jenkins has no Kubernetes deployment credentials and does not run kubectl or Helm install. Argo CD monitors `helm/habitapp` on main with automated sync, pruning and self-healing. Source changes trigger Jenkins through five-minute SCM polling; values-only deployment commits are excluded from polling to prevent build loops.

## Verified results

Seven tests passed. SonarQube reports Quality Gate Passed, 82.5% coverage, zero open security/reliability/maintainability issues and 0.0% duplication. The wildcard CORS setting was removed and a rejection test added.

The versioned ECR image runs as UID 10001. EKS has two Ready application replicas with probes, resource limits, rolling updates and ALB readiness gates. Argo CD reached Synced/Healthy. A missing-image rollout was observed, then a Git revert restored the working image. Timestamped endpoint checks returned HTTP 200 and readiness UP before, during and after the exercise; these are observations, not continuous availability measurements.

## Build and validate

```sh
mvn -B -ntp clean verify
docker build -t habitapp:local .
docker run --rm --memory 512m --read-only --tmpfs /tmp:rw,nosuid,size=128m --cap-drop ALL --security-opt no-new-privileges -p 127.0.0.1:8081:8080 habitapp:local
helm lint helm/habitapp
helm template habitapp helm/habitapp
```

The application listens on container port 8080. Health endpoints are `/actuator/health/liveness` and `/actuator/health/readiness`; `/actuator/info` exposes the deployed image version. Open `http://localhost:8081` for the local container.

## Repository guide

| Path | Purpose |
|---|---|
| src/, pom.xml | Java application, seven tests and JaCoCo |
| Dockerfile, .dockerignore | Multi-stage non-root runtime |
| Jenkinsfile, ci/ | Maven CI, registry publication and GitOps update |
| helm/habitapp/ | Configurable Deployment, Service, ConfigMap, PDB and ALB ingress |
| argocd/ | Restricted AppProject, Application and pinned controller configuration |
| k8s/ | Namespace and Argo CD RBAC bootstrap |
| monitoring/habitapp.json | CPU, memory, pod state and restart dashboard |
| infra/ci-ecr-policy.json | Repository-scoped CI AWS permissions |
| docs/ | Deployment runbook and verified outcomes |

## Credentials and scope

Jenkins credentials: `habitapp-sonar-token` (project analysis only, 30 days), `habitapp-aws-ci` (this ECR repository only) and `habitapp-git` (this GitHub repository's SSH deploy key). No values are committed. Argo CD can write the permitted resource kinds only in namespace habitapp; it cannot modify BlogReact or create cluster roles. Workload service-account tokens are disabled.

## Demonstration limits

The supplied application stores data in memory per replica, has no authentication and loses data on restart. ALB session affinity supports a browser session but does not replicate data or provide database persistence. Use synthetic data only. The public demonstration is HTTP. The supplied Spring Boot 3.3.4 dependency baseline still requires maintenance before production use.

See [deployment](docs/DEPLOYMENT.md) and [validation](docs/VALIDATION.md) for execution details. Local Jenkins, SonarQube and private dashboard forwards must be running for their respective tools; AWS resources remain billable while retained.

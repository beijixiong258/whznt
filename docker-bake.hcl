target "backend" {
  context = "./backend"
}

target "frontend" {
  context = "./frontend"
}

target "release" {
  context = "./deploy"
  contexts = {
    backend = "target:backend"
    frontend = "target:frontend"
  }
  platforms = ["linux/amd64"]
  tags = ["whznt:latest"]
}

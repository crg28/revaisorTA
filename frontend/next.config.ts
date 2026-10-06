import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server bundle, keeps the Docker image small.
  output: "standalone",
};

export default nextConfig;

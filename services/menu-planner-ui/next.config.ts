import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  serverExternalPackages: ["lightningcss"],
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "production-media.gousto.co.uk",
        pathname: "/**",
      },
      {
        protocol: "https",
        hostname: "s3-eu-west-1.amazonaws.com",
        pathname: "/**"
      }
    ],
  },
};

export default nextConfig;

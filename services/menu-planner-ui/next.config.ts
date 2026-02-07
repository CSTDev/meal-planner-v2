import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "production-media.gousto.co.uk",
        pathname: "/**",
      },
    ],
  },
};

export default nextConfig;

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  async rewrites() {
    const adminApiUrl = process.env.NEXT_PUBLIC_ADMIN_API_URL || 'http://localhost:8082';
    return [
      {
        source: '/api/admin/:path*',
        destination: `${adminApiUrl}/api/admin/:path*`,
      },
    ];
  },
};

export default nextConfig;

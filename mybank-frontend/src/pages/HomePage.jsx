import React from 'react';
import { Link } from 'react-router-dom';

function HomePage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-6 text-center bg-gray-50">
      <h1 className="text-4xl font-bold text-bank-heading mb-4">Welcome to MyBank</h1>
      <p className="text-bank-text text-lg mb-8 max-w-lg">
        A secure platform for managing your finances.
      </p>
      <div className="flex gap-4 mb-12">
        <Link to="/login" className="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition font-semibold">
          Login
        </Link>
        <Link to="/register" className="px-6 py-3 bg-white text-bank-text rounded-lg border border-gray-300 hover:bg-gray-100 transition font-semibold">
          Register
        </Link>
      </div>

      <div className="max-w-2xl w-full bg-white p-8 rounded-lg shadow-sm border border-gray-200 text-left">
        <h2 className="text-2xl font-semibold text-bank-heading mb-4">How to Run</h2>
        <ol className="list-decimal list-inside text-bank-text space-y-2 mb-6">
          <li>Run backend: <code className="bg-gray-100 px-1 rounded">./mvnw spring-boot:run</code></li>
          <li>Run frontend: <code className="bg-gray-100 px-1 rounded">cd mybank-frontend && npm run dev</code></li>
        </ol>
        
        <h2 className="text-2xl font-semibold text-bank-heading mb-4">Admin Access</h2>
        <p className="text-bank-text mb-2">Use these credentials to test admin features:</p>
        <div className="bg-gray-100 p-4 rounded-lg font-mono text-sm text-gray-800">
          <p>Email: admin@mybank.com</p>
          <p>Password: adminpassword</p>
        </div>
      </div>
    </div>
  );
}

export default HomePage;

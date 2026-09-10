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
        <h2 className="text-2xl font-semibold text-bank-heading mb-4">Account Verification</h2>
        <p className="text-bank-text mb-4">
          For security and compliance, all new accounts are created with a pending status.
        </p>
        <p className="text-bank-text mb-4">
          To activate your account, an administrator must review your registration. Once verified, you will gain full access to all banking features.
        </p>
        <p className="text-bank-text">
          If you are evaluating this platform, you can test administrative features using the following credentials:
        </p>
        <div className="mt-4 bg-gray-100 p-4 rounded-lg font-mono text-sm text-gray-800">
          <p>Admin Email: admin@mybank.com</p>
          <p>Admin Password: adminpassword</p>
        </div>
      </div>
    </div>
  );
}

export default HomePage;

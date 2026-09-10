import React from 'react';
import { useLocation } from 'react-router-dom';
import DepositForm from '../components/DepositForm';

const DepositPage = () => {
    const location = useLocation();
    const { accountNumber } = location.state || {};
    
    return (
        <div className="flex items-center justify-center min-h-screen bg-gray-100">
            <DepositForm accountNumber={accountNumber} />
        </div>
    );
};

export default DepositPage;

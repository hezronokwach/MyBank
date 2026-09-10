import React from 'react';
import { useLocation } from 'react-router-dom';
import WithdrawForm from '../components/WithdrawForm';

const WithdrawPage = () => {
    const location = useLocation();
    const { accountNumber } = location.state || {};
    
    return (
        <div className="flex items-center justify-center min-h-screen bg-gray-100">
            <WithdrawForm accountNumber={accountNumber} />
        </div>
    );
};

export default WithdrawPage;

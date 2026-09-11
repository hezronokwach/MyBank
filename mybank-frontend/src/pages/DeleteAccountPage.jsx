import { useLocation } from 'react-router-dom';
import DeleteAccountForm from '../components/DeleteAccountForm';
import Layout from '../components/Layout';
import { Link } from 'react-router-dom';

const DeleteAccountPage = () => {
    const location = useLocation();
    const { accountNumber } = location.state || {};
    
    return (
        <Layout><div className="form-page"><Link to="/dashboard" className="back-link">← Back to dashboard</Link><DeleteAccountForm accountNumber={accountNumber} /></div></Layout>
    );
};

export default DeleteAccountPage;

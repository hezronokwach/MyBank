import RegisterForm from '../components/RegisterForm';
import { Link } from 'react-router-dom';

const RegisterPage = () => {
    return (
        <div className="auth-page">
            <Link to="/" className="brand">MyBank</Link>
            <RegisterForm />
        </div>
    );
};

export default RegisterPage;

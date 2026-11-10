import Layout from '../components/Layout';
import CreateAccountForm from '../components/CreateAccountForm';

const CreateAccountPage = () => {
    return (
        <Layout access="customer">
            <div className="max-w-md mx-auto py-10">
                <CreateAccountForm />
            </div>
        </Layout>
    );
};

export default CreateAccountPage;

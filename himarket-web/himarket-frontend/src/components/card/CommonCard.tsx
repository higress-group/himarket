import React from 'react';
import { Link } from 'react-router-dom';

interface FunModelCardProps {
  children?: React.ReactNode;
  to: string;
}

const CommonCard: React.FC<FunModelCardProps> = ({ children, to }) => {
  return (
    <Link to={to} className="h-full block">
      <div
        className='rounded-lg h-full relative overflow-hidden transition-all duration-500 ease-out hover:-translate-y-2 hover:shadow-2xl group'
        style={{ boxShadow: "0px 8px 36px 0px rgba(71, 71, 235, 0.32)" }}
      >{children}</div>
    </Link>
  );
};

export default CommonCard;
